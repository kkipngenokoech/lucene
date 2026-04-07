import pytest
from datetime import datetime
from src.core.database import Database
from src.core.models import User, Task


class TestDatabase:
    @pytest.fixture
    def db(self):
        database = Database(":memory:")
        yield database
        database.close()

    def test_create_user(self, db):
        user = db.create_user("testuser", "test@example.com")
        
        assert user.id is not None
        assert user.username == "testuser"
        assert user.email == "test@example.com"
        assert isinstance(user.created_at, datetime)
        assert user.is_active is True

    def test_get_user(self, db):
        created_user = db.create_user("testuser", "test@example.com")
        retrieved_user = db.get_user(created_user.id)
        
        assert retrieved_user is not None
        assert retrieved_user.id == created_user.id
        assert retrieved_user.username == created_user.username
        assert retrieved_user.email == created_user.email

    def test_get_nonexistent_user(self, db):
        user = db.get_user(999)
        assert user is None

    def test_create_task(self, db):
        user = db.create_user("testuser", "test@example.com")
        task = db.create_task(
            title="Test Task",
            description="A test task",
            user_id=user.id
        )
        
        assert task.id is not None
        assert task.title == "Test Task"
        assert task.description == "A test task"
        assert task.user_id == user.id
        assert isinstance(task.created_at, datetime)
        assert task.completed is False
        assert task.due_date is None

    def test_create_task_with_due_date(self, db):
        user = db.create_user("testuser", "test@example.com")
        due_date = datetime(2024, 12, 31)
        
        task = db.create_task(
            title="Test Task",
            description="A test task",
            user_id=user.id,
            due_date=due_date
        )
        
        assert task.due_date == due_date

    def test_get_tasks_by_user(self, db):
        user = db.create_user("testuser", "test@example.com")
        
        task1 = db.create_task("Task 1", "First task", user.id)
        task2 = db.create_task("Task 2", "Second task", user.id)
        
        tasks = db.get_tasks_by_user(user.id)
        
        assert len(tasks) == 2
        task_ids = [task.id for task in tasks]
        assert task1.id in task_ids
        assert task2.id in task_ids

    def test_get_tasks_by_user_empty(self, db):
        user = db.create_user("testuser", "test@example.com")
        tasks = db.get_tasks_by_user(user.id)
        
        assert tasks == []

    def test_get_tasks_by_nonexistent_user(self, db):
        tasks = db.get_tasks_by_user(999)
        assert tasks == []
